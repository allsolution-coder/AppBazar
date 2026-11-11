import { useState } from "react";
import QRCode from "qrcode";
import { api } from "./api";

/** Pay in rupees with any UPI app: shows a QR code, then asks for the transaction id so an admin can check it. */
export default function UpiPay({ kind, productIds, onSubmitted }) {
  const [step, setStep] = useState("start"); // start, pay
  const [data, setData] = useState(null);
  const [qr, setQr] = useState("");
  const [utr, setUtr] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  async function start() {
    setBusy(true); setErr("");
    try {
      const d = await api("/upi/start", { method: "POST", body: { kind, productIds } });
      setData(d);
      setQr(await QRCode.toDataURL(d.upiLink, { width: 260, margin: 1 }));
      setStep("pay");
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setErr("");
    try {
      await api(`/upi/${data.payment.id}/submit`, { method: "POST", body: { utr } });
      onSubmitted();
    } catch (x) { setErr(x.message); } finally { setBusy(false); }
  }

  if (step === "start") return (
    <div>
      <p className="tag">Pay in rupees with Google Pay, PhonePe, Paytm or any UPI app. You scan a QR code, then enter the transaction ID. We check the payment before it is accepted.</p>
      {err && <p className="err">{err}</p>}
      <button disabled={busy} onClick={start}>{busy ? "Please wait…" : "Pay with UPI QR"}</button>
    </div>
  );

  const p = data.payment;
  return (
    <div>
      <h3>Pay ₹{p.amountInr}</h3>
      <p className="tag">This is ${p.amountUsd} in rupees. Pay exactly this amount.</p>
      <img src={qr} alt="UPI QR code" width="260" height="260" style={{ background: "#fff", borderRadius: 12, padding: 6 }} />
      <p className="tag">Or pay to UPI ID <b>{data.upiId}</b> ({data.payee}). On a phone you can <a href={data.upiLink}>open your UPI app</a>.</p>
      <ol style={{ paddingLeft: 20 }}>
        <li>Scan the QR code and pay ₹{p.amountInr}.</li>
        <li>After paying, copy the <b>UTR</b> or <b>UPI reference number</b> from your payment app (usually 12 digits).</li>
        <li>Type it below and press "I have paid".</li>
      </ol>
      <form onSubmit={submit}>
        <label>Transaction ID (UTR)
          <input value={utr} onChange={(e) => setUtr(e.target.value)} minLength={8} maxLength={30} required placeholder="e.g. 412345678901" />
        </label>
        <span className="tag">Your reference code is {p.reference}. A wrong or fake ID will be rejected.</span>
        {err && <span className="err">{err}</span>}
        <button disabled={busy}>{busy ? "Sending…" : "I have paid"}</button>
      </form>
    </div>
  );
}
